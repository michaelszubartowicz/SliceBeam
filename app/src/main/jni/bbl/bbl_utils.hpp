#ifndef BBL_UTILS_HPP
#define BBL_UTILS_HPP

#include "libslic3r/Point.hpp"

typedef enum {
    eNormal,  // normal face
    eSmallOverhang,  // small overhang
    eSmallHole,      // face with small hole
    eExteriorAppearance,  // exterior appearance
    eMaxNumFaceTypes
}EnumFaceTypes;

struct FaceProperty
{   // triangle face property
    EnumFaceTypes type;
    double area;
    // stl_normal normal;

    std::string to_string() const
    {
        std::string str;
        // skip normal type facet to improve performance
        if (type > eNormal && type < eMaxNumFaceTypes) {
            str += std::to_string(type);
            if (area != 0.f)
                str += " " + std::to_string(area);
        }
        return str;
    }

    void from_string(const std::string& str)
    {
        std::string val_str, area_str;
        do {
            if (str.empty())
                break;

            this->type = (EnumFaceTypes)std::atoi(str.c_str());
            if (this->type <= eNormal || this->type >= eMaxNumFaceTypes)
                break;

            size_t type_end_pos = str.find(" ");
            if (type_end_pos == std::string::npos) {
                this->area = 0.f;
                return;
            }

            area_str = str.substr(type_end_pos + 1);
            if (!area_str.empty())
                this->area = std::atof(area_str.c_str());
            else
                this->area = 0.f;
            return;
        } while (0);

        this->type = eNormal;
        this->area = 0.f;
    }
};


namespace Slic3r {
    namespace Geometry {
        void rotation_from_two_vectors(Vec3d& from, Vec3d to, Vec3d& rotation_axis, double& phi, Matrix3d* rotation_matrix) {
            const Matrix3d m = Eigen::Quaterniond().setFromTwoVectors(from, to).toRotationMatrix();
            const Eigen::AngleAxisd aa(m);
            rotation_axis = aa.axis();
            phi           = aa.angle();
            if (rotation_matrix)
                *rotation_matrix = m;
        }

        Vec3d extract_euler_angles(const Eigen::Matrix<double, 3, 3, Eigen::DontAlign>& rotation_matrix) {
            // The extracted "rotation" is a triplet of numbers such that Geometry::rotation_transform
            // returns the original transform. Because of the chosen order of rotations, the triplet
            // is not equivalent to Euler angles in the usual sense.
            Vec3d angles = rotation_matrix.eulerAngles(2,1,0);
            std::swap(angles(0), angles(2));
            return angles;
        }
    }

    double area_of_boundingbox(BoundingBoxf3 bb) {
        return double(bb.max(0) - bb.min(0)) * (bb.max(1) - bb.min(1));
    }

    stl_vertex get_its_vertex(indexed_triangle_set& its, int facet_idx, int vertex_idx) {
        return its.vertices[its.indices[facet_idx][vertex_idx]];
    }

    float get_its_facet_area(indexed_triangle_set& its, int facet_idx) {
        return std::abs((get_its_vertex(its, facet_idx, 0) - get_its_vertex(its, facet_idx, 1))
                                .cross(get_its_vertex(its, facet_idx, 0) - get_its_vertex(its, facet_idx, 2)).norm()) / 2;
    }

    void rotate_model_instance(ModelInstance* obj, Matrix3d& rotation_matrix) {
        auto m_transformation = obj->get_transformation();
        auto rotation = m_transformation.get_rotation_matrix();
        rotation      = rotation_matrix * rotation;
        obj->set_rotation(Geometry::Transformation(rotation).get_rotation());
    }
}

#endif //BBL_UTILS_HPP
